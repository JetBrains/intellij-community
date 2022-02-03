#include <stdio.h>
#include <arpa/inet.h>
#include <ifaddrs.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <pthread.h>
#include <strings.h>
#include <errno.h>


// See svg file and wslproxy_test_client.py

// When started, prints egress (eth0) IP addr as 4 bytes. Then, 2 bytes of ingress (loopback) port.
// App running on WSL connects to this port.
// Tool then opens egress (eth0) port and prints it as 2 bytes.
// Windows client connects to it and talks to WSL app connected to the ingress port.
// When stdout is closed or SIGINT sent, process stops.

// Threads are unbound, but it should not be a problem unless you create lots of connections


// Will listen egress in this port
#define JB_EGRESS_INTERFACE "eth0"

// Egress IP
static in_addr_t g_egress_ip;

// Bind to eth0 only
static in_addr_t jb_get_wsl_public_ip() {
    struct ifaddrs *ifaddrs_p_base;
    if (getifaddrs(&ifaddrs_p_base) != 0) {
        perror("getifaddrs failed");
        exit(-1);
    }
    for (struct ifaddrs *ifaddrs_p = ifaddrs_p_base; ifaddrs_p != NULL; ifaddrs_p = ifaddrs_p->ifa_next) {
        // Search for interface and ipv4
        if ((ifaddrs_p->ifa_addr->sa_family != AF_INET) ||
            ((strcmp(ifaddrs_p->ifa_name, JB_EGRESS_INTERFACE) != 0))) {
            continue;
        }
        const struct sockaddr_in *in_addr = (struct sockaddr_in *) ifaddrs_p->ifa_addr;
        const in_addr_t result = in_addr->sin_addr.s_addr;
        freeifaddrs(ifaddrs_p_base);
        return result;
    }
    freeifaddrs(ifaddrs_p_base);
    fprintf(stderr, "No interface %s found", JB_EGRESS_INTERFACE);
    exit(-1);
}

// Creates server socket, returns its descriptor and opened port via pointer
static int jb_create_srv_socket(const in_addr_t listen_to, uint16_t *port) {
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
    *port = ntohs(addr_p.sin_port);
    return sock;
}


// runs command in separate thread
// thread is detached so it just dies when command finishes: no need to join it
static void jb_launch_in_thread(void *(command)(void *), void *argument) {
    pthread_attr_t attr;
    pthread_attr_init(&attr);
    pthread_attr_setdetachstate(&attr, PTHREAD_CREATE_DETACHED);

    pthread_t thread;
    const int result = pthread_create(&thread, &attr, (void *(*)(void *)) command, argument);
    if (result != 0) {
        fprintf(stderr, "pthread_create failed: %d", result);
        exit(result);
    }
}

// Pair of socket to connect
typedef struct {
    int src_socket_fd; // read from here
    int dest_socket_fd; // write here
} jb_sockpair;


// connects two sockets: read from src, write to dst.
// struct must live in heap: it will be freed at the end
// it closes only source, but since you have two pairs each source will be closed by appropriate function
static void *jb_connect_pair(jb_sockpair *sockpair) {
    const int source = sockpair->src_socket_fd;
    const int dest = sockpair->dest_socket_fd;
    free(sockpair);
    char buf[64800]; //MTU 1500 + 60 bytes TCP+IP header * 45 (because can't be greater than TCP max window size)
    ssize_t bytes;
    while ((bytes = read(source, buf, sizeof(buf))) > 0) {
        ssize_t sent = 0;
        ssize_t write_result;
        while (sent < bytes) {
            if ((write_result = write(dest, buf + sent, bytes - sent)) < 0) {
                if (errno != EINTR || errno != EAGAIN) {
                    break; //socket closed
                }
            }
            sent += write_result;
        }
    }
    close(source);
    return NULL;
}

// accepts server socket, returns client socket
static int jb_accept(const int srv_sock_fd) {
    const int client_sock_fd = accept(srv_sock_fd, NULL, NULL);
    if (client_sock_fd < 0) {
        perror("Can't accept connection");
        return -1;
    }
    return client_sock_fd;
}


// creates structure for pair of sockets in heap
static jb_sockpair *jb_create_sockpair(const int src_fd, const int dst_fd) {
    jb_sockpair *pair = malloc(sizeof(jb_sockpair));
    pair->src_socket_fd = src_fd;
    pair->dest_socket_fd = dst_fd;
    return pair;
}


// Listens for ingress server socket
// on each connection creates egress server socket and connects it with client socket
// As detached thread may run forever
_Noreturn static void *jb_listen_ingress(const int *p_ingress_srv_sock_fd) {
    while (1) {
        const int ingress_client_sock_fd = jb_accept(*p_ingress_srv_sock_fd);
        if (ingress_client_sock_fd < 0) {
            continue; // Error logged by jb_accept
        }

        uint16_t egress_port;
        const int egress_srv_sock_fd = jb_create_srv_socket(g_egress_ip, &egress_port);
        write(STDOUT_FILENO, &egress_port, sizeof egress_port);

        const int egress_client_sock_fd = jb_accept(egress_srv_sock_fd);
        close(egress_srv_sock_fd);
        if (egress_srv_sock_fd < 0) {
            continue; // Error logged by jb_accept
        }
        jb_sockpair *egress_to_ingress = jb_create_sockpair(egress_client_sock_fd, ingress_client_sock_fd);
        jb_sockpair *ingress_to_egress = jb_create_sockpair(ingress_client_sock_fd, egress_client_sock_fd);

        jb_launch_in_thread((void *(*)(void *)) &jb_connect_pair, egress_to_ingress);
        jb_launch_in_thread((void *(*)(void *)) &jb_connect_pair, ingress_to_egress);
    }
}

static int g_ingress_srv_sock_fd;

int main() {
    g_egress_ip = jb_get_wsl_public_ip();

    // IP address
    write(STDOUT_FILENO, &g_egress_ip, sizeof g_egress_ip);

    // Open ingress port and report to Intellij
    uint16_t ingress_port;
    g_ingress_srv_sock_fd = jb_create_srv_socket(htonl(INADDR_LOOPBACK), &ingress_port);
    // Ingress server port
    write(STDOUT_FILENO, &ingress_port, sizeof ingress_port);
    jb_launch_in_thread((void *(*)(void *)) &jb_listen_ingress, &g_ingress_srv_sock_fd);

    while (getchar() != EOF);
    return 0;
}
