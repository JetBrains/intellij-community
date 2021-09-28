#include <stdio.h>
#include <arpa/inet.h>
#include <ifaddrs.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <stdbool.h>
#include <pthread.h>
#include <strings.h>
#include <sys/select.h>

// Output: [linux-ip] [external-port] [internal-port]
// Connect to the external from Windows and to the internal from Linux
// Sockets are connected from that moment until byte written to the stdin or SIGINT/SIGHUP sent

#define JB_WSL_INTERFACE "eth0"

// Bind to eth0 only
static in_addr_t jb_get_wsl_public_ip() {
    struct ifaddrs *ifaddrs_p_base;
    if (getifaddrs(&ifaddrs_p_base) != 0) {
        perror("getifaddrs failed");
        exit(-1);
    }
    for (struct ifaddrs *ifaddrs_p = ifaddrs_p_base; ifaddrs_p != NULL; ifaddrs_p = ifaddrs_p->ifa_next) {
        // Search for inteface and ipv4
        if ((ifaddrs_p->ifa_addr->sa_family != AF_INET) ||
            ((strcmp(ifaddrs_p->ifa_name, JB_WSL_INTERFACE) != 0))) {
            continue;
        }
        const struct sockaddr_in *in_addr = (struct sockaddr_in *) ifaddrs_p->ifa_addr;
        const in_addr_t result = in_addr->sin_addr.s_addr;
        freeifaddrs(ifaddrs_p_base);
        return result;
    }
    freeifaddrs(ifaddrs_p_base);
    fprintf(stderr, "No interface %s found", JB_WSL_INTERFACE);
    exit(-1);
}

// Open internal or external sock, print addr if print_addr
static int jb_open_socket(const in_addr_t listen_to, const bool print_addr) {
    const int sock = socket(AF_INET, SOCK_STREAM, 0);
    if (sock < 0) {
        perror("can't open socket");
        exit(-1);
    }
    struct sockaddr_in addr_p = {0};
    addr_p.sin_family = AF_INET;
    addr_p.sin_addr.s_addr = listen_to;
    if (bind(sock, (struct sockaddr *) &addr_p, sizeof(struct sockaddr_in)) != 0) {
        perror("can't bind to port");
        exit(-1);
    }
    if (listen(sock, 1) != 0) {
        perror("socket can't be listen");
        exit(-1);
    }
    bzero(&addr_p, sizeof(addr_p));
    socklen_t size = sizeof(addr_p);
    if (getsockname(sock, (struct sockaddr *) &addr_p, &size) != 0) {
        perror("getsockname failed");
        exit(-1);
    }

    if (print_addr) {
        printf("%s ", inet_ntoa(addr_p.sin_addr));
    }
    printf("%d ", ntohs(addr_p.sin_port));

    return sock;
}

struct jb_socket_info {
    int server_sock_fd; // Listening server socket
    int client_sock_fd; // Connected (accepted) client
};

// public (external) socket and local (internal)
static struct jb_socket_info jb_public = {0}, jb_local = {0};

// marks socket for the next "select" call if client hasn't been connected yet
static void jb_set_select_socket(const struct jb_socket_info *sock, fd_set *sock_set, int *max_sock) {
    if (sock->client_sock_fd != 0) { // Client already connected
        return;
    }
    FD_SET(sock->server_sock_fd, sock_set); // select socket
    if (sock->server_sock_fd > *max_sock) {
        *max_sock = sock->server_sock_fd;
    }
}

static void jb_set_client_socket(const fd_set *sock_set, struct jb_socket_info *sock) {
    if (sock->client_sock_fd == 0 && FD_ISSET(sock->server_sock_fd, sock_set)) {
        if ((sock->client_sock_fd = accept(sock->server_sock_fd, NULL, NULL)) < 0) {
            perror("accept call failed");
            exit(-1);
        }
        close(sock->server_sock_fd); // Client connected, close listening socket
        sock->server_sock_fd = 0;
    }
}

// Waits for both clients to connect
static void jb_connect_accept_clients() {
    fd_set sock_set;
    struct timeval time = {.tv_sec = 120, .tv_usec = 0};

    const int stdin_fd = fileno(stdin);
    while (jb_public.client_sock_fd == 0 || jb_local.client_sock_fd == 0) {
        FD_ZERO(&sock_set);
        FD_SET(stdin_fd, &sock_set);
        int max_sock = stdin_fd;
        jb_set_select_socket(&jb_local, &sock_set, &max_sock);
        jb_set_select_socket(&jb_public, &sock_set, &max_sock);
        if (select(max_sock + 1, &sock_set, NULL, NULL, &time) < 0) {
            perror("select call failed");
            exit(-1);
        }
        if (FD_ISSET(stdin_fd, &sock_set)) {
            exit(0);
        }
        jb_set_client_socket(&sock_set, &jb_local);
        jb_set_client_socket(&sock_set, &jb_public);
    }
}

// Connect source (sock) and destination (another socket)
static void *jb_connect(const struct jb_socket_info *sock) {
    const int source = sock->client_sock_fd;
    const int dest = (sock == &jb_local ? jb_public : jb_local).client_sock_fd;
    char buf[4096];
    size_t bytes;
    while ((bytes = read(source, buf, sizeof(buf))) > 0) {
        write(dest, buf, bytes);
    }
    exit(0);
}

static void jb_create_thread(struct jb_socket_info *sock) {
    pthread_t thread;
    const int result = pthread_create(&thread, NULL, (void *(*)(void *)) &jb_connect, sock);
    if (result != 0) {
        fprintf(stderr, "pthread_create failed: %d", result);
        exit(result);
    }
}

int main() {
    jb_public.server_sock_fd = jb_open_socket(jb_get_wsl_public_ip(), true);
    jb_local.server_sock_fd = jb_open_socket(htonl(INADDR_LOOPBACK), false);

    puts("\n");
    fflush(stdout);

    jb_connect_accept_clients();


    jb_create_thread(&jb_public);
    jb_create_thread(&jb_local);

    getchar();
    return 0;
}
