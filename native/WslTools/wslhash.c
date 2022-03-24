#define XXH_VECTOR XXH_SSE2
#define XXH_STATIC_LINKING_ONLY 1

#include <stdio.h>

#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include "xxhash.h"
#include <ftw.h>
#include <fcntl.h>
#include <sys/mman.h>
#include <limits.h>
#include <errno.h>
#include <locale.h>
#include <langinfo.h>

// wslhash folder [ext1 ext2 ..]
// Calculates hashes for all files in this folder (optionally limit by extensions)
// output is: file:[hash]file[hash]
// hash is 8 bytes little endian

static size_t g_dir_len = 0; // Length of dir string
static char **g_exts; // list of extensions or NULL if no filter needed
static int g_num_of_exts = 0; // number of extensions


static const char empty[sizeof(XXH64_hash_t)];

// Check is file extension ok or should be skipped
static int file_extension_ok(const char *file) {
    if (g_num_of_exts == 0) return 1;
    const char *dot = strrchr(file, '.');
    if (!dot) return 1; // No extension
    for (int i = 0; i < g_num_of_exts; i++) {
        if (strcmp(dot + 1, g_exts[i]) == 0) {
            return 1;
        }
    }

    return 0;
}

// Called on each file
static int
process_file(const char *fpath, const struct stat *sb, int tflag, __attribute__((unused)) struct FTW *ftwbuf) {
    if (tflag != FTW_F) {
        return 0; // Not a file
    }
    if (!file_extension_ok(fpath)) {
        return 0; // File has wrong extension, skip
    }

    const char *file_name = fpath + g_dir_len + 1; // remove first "/"
    printf("%s:", file_name);
    if (sb->st_size == 0) {
        // No need to calculate hash for empty file
        fwrite(empty, sizeof(empty), 1, stdout);
        return 0;
    }
    const int fd = open(fpath, O_RDONLY);
    if (fd == -1) {
        fprintf(stderr, "Can't open file %s", fpath);
        perror("Can't open file");
        exit(2);
    }

    // Mmap file and calculate hash
    char *buffer;
    buffer = mmap(NULL, sb->st_size, PROT_READ, MAP_FILE | MAP_PRIVATE, fd, 0);
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
    return 0;
}

static int ensure_charset() {
    setlocale(LC_CTYPE, "");
    const char *charset = nl_langinfo(CODESET);

    if (strncmp(charset, "UTF-8", sizeof "UTF-8") == 0) {
        // Java side decodes output as UTF-8 and almost all WSL distros use UTF
        return 1;
    }
    if (strncmp(charset, "ASCII", sizeof "ASCII") == 0) {
        // ASCII is 7 bit, so english texts could be decoded by java either
        return 1;
    }
    // Other charsets aren't used nor supported by WSL
    fprintf(stderr, "Please use UTF-8 locale, not %s", charset);
    return 0;
}

int main(int argc, char *argv[]) {
    if (!ensure_charset()) {
        return -1;
    }

    if (argc < 2) {
        fprintf(stderr, "No path provided");
        return 1;
    }
    char *root_dir = argv[1];
    struct stat path_stat;
    stat(root_dir, &path_stat);
    if (!S_ISDIR(path_stat.st_mode)) {
        fprintf(stderr, "Provided path is not dir\n");
        return 2;
    }

    char root_dir_clean[PATH_MAX];
    if (realpath(root_dir, root_dir_clean) == NULL) {
        fprintf(stderr, "realpath failed: %d", errno);
        return -1;
    }

    g_dir_len = strlen(root_dir_clean);

    const int args_before_exts = 2;
    if (argc > args_before_exts) { // Extensions are provided: store argc+argv
        g_exts = argv + args_before_exts;
        g_num_of_exts = argc - args_before_exts;
    }


    // number of file descriptors is more or less random taken from example
    // we don't know how many descriptors are available on the particular WSL, but sure not less than 20
    if (nftw(root_dir_clean, process_file, 20, FTW_MOUNT) == -1) { // Walk through files, see nftw(3)
        perror("nftw failed");
        return 3;
    }
    return 0;
}
