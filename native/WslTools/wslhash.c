#define XXH_VECTOR XXH_SSE2
#define XXH_STATIC_LINKING_ONLY 1

#include <stdio.h>
#include <limits.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include "xxhash.h"
#include <ftw.h>
#include <fcntl.h>
#include <sys/mman.h>
#include <errno.h>
#include <locale.h>
#include <langinfo.h>
#include <stdbool.h>
#include <stdarg.h>
#include <regex.h>

// Usage:
//  wslhash [OPTIONS] DIR
//
// Options:
//  -n
//    skip the hash calculation step.
//  -f FILTER
//    filters the files using the given FILTER. May be specified multiple times.
//  -s
//    report files for stubbing e.g. files that exists, but were filtered out (explicitly or implicitly).
//
// Description:
//   Calculate hashes (unless `-n`) for all files in the given DIR.
//   Files can be filtered using `-f` option.
//
// Filters:
//   Each filter must be specified in the following format:
//     OPERATOR:MATCHER:PATTERN
//       where OPERATOR is one of:
//         `-` to exclude
//         `+` to include
//       and MATCHER is one of:
//         `rgx` for matching using extended regular expressions in PATTERN (see regex(3))
//
//   The combination of OPERATORS dictates the filtering behavior:
//     only `-`, means process all files that do not match any excludes
//     only `+`, means process only files that match any includes
//     both `+` and `-`, mean process all files that do not match any excludes or match any includes
//
//   Filters within each OPERATOR group are processed in the order of appearance in the command line.
//
// Output format:
//   [FILE_PATH]\0[HASH]
//     where HASH is little-endian 8 byte (64 bit) integer
//   [LINK_PATH]\1[LINK_LEN][LINK]
//     where LINK_LEN is 4 byte (32 bit) signed int
//   [STUB_PATH]\2

//#define WSLHASH_DEBUG 1
#ifdef WSLHASH_DEBUG
#define DEBUG_PRINTF(fmt, ...) \
        do { fprintf(stderr, "%s:%d:%s: " fmt, __FILE__, __LINE__, __func__, __VA_ARGS__); } while (0)
#else
#define DEBUG_PRINTF(fmt, ...)
#endif // WSLHASH_DEBUG

#define STRINGIFY_(a) #a
#define STRINGIFY(a) STRINGIFY_(a)

#define FLT_N_MAX 50
#define FLT_MATCHER_LEN_MAX 3
#define FLT_PATTERN_LEN_MAX 64
#define FLT_NAME_LEN_MAX (1 + FLT_MATCHER_LEN_MAX + FLT_PATTERN_LEN_MAX + 2) // OPERATOR + MATCHER + PATTERN + delims
#define FLT_SCAN_FMT "%c:%" STRINGIFY(FLT_MATCHER_LEN_MAX) "s:%" STRINGIFY(FLT_PATTERN_LEN_MAX) "s"

#define FILE_SEPARATOR 0
#define LINK_SEPARATOR 1
#define STUB_SEPARATOR 2

struct wslhash_filter_t {
    char name[FLT_NAME_LEN_MAX + 1]; // full filter name (OPERATOR:MATCHER:PATTERN).

    void *pattern; // points to arbitrary pattern object.

    int (*fn_match)(const struct wslhash_filter_t *, const char *); // returns 1 if matches, 0 otherwise.

    void (*fn_init)(struct wslhash_filter_t *, const char *); // initializes the filter.

    void (*fn_free)(const struct wslhash_filter_t *); // destroys the filter.
};

struct wslhash_options_t {
    char root_dir[PATH_MAX];
    size_t root_dir_len;

    struct wslhash_filter_t excludes[FLT_N_MAX];
    size_t excludes_len;

    struct wslhash_filter_t includes[FLT_N_MAX];
    size_t includes_len;

    int skip_hash;
    int report_stubs;
};


static const char EMPTY[sizeof(XXH64_hash_t)] = {0};

static struct wslhash_options_t g_options = {0};


static void free_all(void) {
    const struct wslhash_filter_t *filter;
    for (size_t i = 0; i < g_options.excludes_len; i++) {
        filter = &g_options.excludes[i];
        filter->fn_free(filter);
    }
    for (size_t i = 0; i < g_options.includes_len; i++) {
        filter = &g_options.includes[i];
        filter->fn_free(filter);
    }
}

static int any_match(const struct wslhash_filter_t *filters, const size_t filter_len, const char *filename) {
    const struct wslhash_filter_t *filter;
    for (size_t i = 0; i < filter_len; i++) {
        filter = &filters[i];
        if (filter->fn_match(filter, filename)) {
            DEBUG_PRINTF("File matched a filter '%s': %s\n", filter->name, filename);
            return true;
        }
    }
    return false;
}

static int is_filename_ok(const char *filename) {
    DEBUG_PRINTF("Checking file: %s\n", filename);
    if (g_options.excludes_len == 0 && g_options.includes_len == 0) {
        return true;
    }
    if (g_options.excludes_len == 0) {
        return any_match(g_options.includes, g_options.includes_len, filename);
    }
    if (g_options.includes_len == 0) {
        return !any_match(g_options.excludes, g_options.excludes_len, filename);
    }
    return !any_match(g_options.excludes, g_options.excludes_len, filename) ||
           any_match(g_options.includes, g_options.includes_len, filename);
}

static int is_dir(const char *path) {
    struct stat stat_info = {0};
    if (stat(path, &stat_info) != 0) {
        return false;
    }
    return S_ISDIR(stat_info.st_mode);
}

static const char *filename(const char *fpath) {
    const char *last_slash = strrchr(fpath, '/');
    return (last_slash != NULL) ? last_slash + 1 : fpath;
}

// Called on each file
static int
process_file(const char *fpath, const struct stat *sb, int tflag, __attribute__((unused)) struct FTW *ftwbuf) {
    DEBUG_PRINTF("Processing file: %s\n", fpath);
    if (tflag != FTW_F && tflag != FTW_SL) {
        DEBUG_PRINTF("Skipping: %s\n", fpath);
        return 0; // Not a file
    }
    const char *fpath_relative = fpath + g_options.root_dir_len + 1; // remove first "/"
    if (tflag == FTW_F) {
        if (!is_filename_ok(filename(fpath))) {
            DEBUG_PRINTF("Excluding file: %s\n", fpath);
            if (g_options.report_stubs) {
                printf("%s%c", fpath_relative, STUB_SEPARATOR);
            }
            return 0;
        }

        printf("%s%c", fpath_relative, FILE_SEPARATOR);
        if (sb->st_size == 0 || g_options.skip_hash) {
            // No need to calculate hash for empty file
            fwrite(EMPTY, sizeof(EMPTY), 1, stdout);
            return 0;
        }
        const int fd = open(fpath, O_RDONLY);
        if (fd == -1) {
            fprintf(stderr, "Can't open file %s", fpath);
            perror("Can't open file");
            exit(2);
        }

        // Mmap file and calculate hash
        char *buffer = mmap(NULL, sb->st_size, PROT_READ, MAP_FILE | MAP_PRIVATE, fd, 0);
        madvise(buffer, sb->st_size, MADV_SEQUENTIAL);
        if (buffer == MAP_FAILED) {
            fprintf(stderr, "Can't mmap file %s", fpath);
            perror("Can't mmap file");
            exit(3);
        }
        XXH64_hash_t hash = XXH64(buffer, sb->st_size, 0);
        fwrite(&hash, sizeof(XXH64_hash_t), 1, stdout);
        munmap(buffer, sb->st_size);

        close(fd);
    } else {
        char real_path[PATH_MAX] = {0};
        if (realpath(fpath, real_path) != NULL && is_dir(real_path)) {
            printf("%s%c", fpath_relative, LINK_SEPARATOR);
            const int32_t len = (int32_t) strlen(real_path);
            fwrite(&len, sizeof(int32_t), 1, stdout);
            fputs(real_path, stdout);
        }
    }
    return 0;
}

static void rgx_init(struct wslhash_filter_t *self, const char *pattern_raw) {
    regex_t *regex = calloc(1, sizeof(regex_t));
    if (!regex) {
        fprintf(stderr, "Calloc failed\n");
        exit(EXIT_FAILURE);
    }
    if (regcomp(regex, pattern_raw, REG_EXTENDED)) {
        fprintf(stderr, "Failed to compile basic regex: %s\n", pattern_raw);
        exit(EXIT_FAILURE);
    }
    self->pattern = regex;
}

static void rgx_free(const struct wslhash_filter_t *self) {
    regex_t *regex = self->pattern;
    regfree(regex);
    free(regex);
}

static int rgx_match(const struct wslhash_filter_t *self, const char *path) {
    const regex_t *regex = self->pattern;
    const int result = regexec(regex, path, 0, NULL, 0);
    if (result == REG_OK) {
        return true;
    }
    if (result == REG_NOMATCH) {
        return false;
    }
    char buf[100] = {0};
    regerror(result, regex, buf, sizeof(buf));
    fprintf(stderr, "Regex match failed: %s\n", buf);
    exit(EXIT_FAILURE);
}

static void parse_filter(const char *arg) {
    char operator;
    char matcher[FLT_MATCHER_LEN_MAX + 1] = {0};
    char pattern_raw[FLT_PATTERN_LEN_MAX + 1] = {0};

    if (sscanf(arg, FLT_SCAN_FMT, &operator, matcher, pattern_raw) < 3) {
        fprintf(stderr, "Invalid filter format: %s\n", arg);
        exit(EXIT_FAILURE);
    }

    struct wslhash_filter_t *filter;

    if (operator == '-') {
        if (g_options.excludes_len >= FLT_N_MAX) {
            fprintf(stderr, "Too many exclude filters >%d\n", FLT_N_MAX);
            exit(EXIT_FAILURE);
        }
        filter = &g_options.excludes[g_options.excludes_len++];
    } else if (operator == '+') {
        if (g_options.includes_len >= FLT_N_MAX) {
            fprintf(stderr, "Too many include filters >%d\n", FLT_N_MAX);
            exit(EXIT_FAILURE);
        }
        filter = &g_options.includes[g_options.includes_len++];
    } else {
        fprintf(stderr, "Unknown filter operator '%c': %s\n", operator, arg);
        exit(EXIT_FAILURE);
    }

    if (strcmp(matcher, "rgx") == 0) {
        filter->fn_init = rgx_init;
        filter->fn_free = rgx_free;
        filter->fn_match = rgx_match;
    } else {
        fprintf(stderr, "Unknown filter matcher '%s': %s\n", matcher, arg);
        exit(EXIT_FAILURE);
    }

    strncpy(filter->name, arg, FLT_NAME_LEN_MAX);
    filter->fn_init(filter, pattern_raw);
}

static void parse_args(int argc, char *argv[]) {
    int c;
    while ((c = getopt(argc, argv, "nsf:")) != -1) {
        switch (c) {
            case 's':
                g_options.report_stubs = 1;
                break;
            case 'n':
                g_options.skip_hash = 1;
                break;
            case 'f':
                parse_filter(optarg);
                break;
            default:
                fprintf(stderr, "Invalid options\n");
                exit(EXIT_FAILURE);
        }
    }
    if (optind >= argc) {
        fprintf(stderr, "Dir is missing\n");
        exit(EXIT_FAILURE);
    }
    const char *dir = argv[optind];
    if (!is_dir(dir)) {
        fprintf(stderr, "Provided path is not root_dir\n");
        exit(2);
    }
    if (realpath(dir, g_options.root_dir) == NULL) {
        fprintf(stderr, "realpath failed: %d", errno);
        exit(-1);
    }
    g_options.root_dir_len = strlen(g_options.root_dir);
}

static int ensure_charset(void) {
    setlocale(LC_CTYPE, "");
    const char *charset = nl_langinfo(CODESET);

    if (strncmp(charset, "UTF-8", sizeof "UTF-8") == 0) {
        // Java side decodes output as UTF-8 and almost all WSL distros use UTF
        return true;
    }
    if (strncmp(charset, "ASCII", sizeof "ASCII") == 0) {
        // ASCII is 7 bit, so english texts could be decoded by java either
        return true;
    }
    // Other charsets aren't used nor supported by WSL
    fprintf(stderr, "Please use UTF-8 locale, not %s", charset);
    return false;
}

int main(int argc, char *argv[]) {
    if (!ensure_charset()) {
        return -1;
    }
    parse_args(argc, argv);
    // number of file descriptors is more or less random taken from example
    // we don't know how many descriptors are available on the particular WSL, but sure not less than 20
    if (nftw(g_options.root_dir, process_file, 20, FTW_MOUNT | FTW_PHYS) == -1) { // Walk through files, see nftw(3)
        perror("nftw failed");
        return 3;
    }
    free_all();
    return EXIT_SUCCESS;
}
