#include <resolv.h>
#include <stdio.h>
#include <arpa/inet.h>
#include <errno.h>

// Returns windows_ip:wsl_ip from WSL-2
// https://docs.microsoft.com/en-us/windows/wsl/wsl2-ux-changes#accessing-network-applications
// See Makefile for compilation
#define IP4_BUF_SIZE (sizeof("AAA.AAA.AAA.AAA"))

int main() {
    char win_ip[IP4_BUF_SIZE] = {};
    char lin_ip[IP4_BUF_SIZE] = {};
    res_init();

    if (_res.nscount != 1) {
        fprintf(stderr, "Wrong number of dns entries: %d", _res.nscount);
        return 1;
    }

    const struct sockaddr_in* win_addr = &_res.nsaddr_list[0];
    if (inet_ntop(AF_INET, &win_addr->sin_addr, win_ip, IP4_BUF_SIZE) == NULL) {
        fprintf(stderr, "Can't convert dns to IP: %d", errno);
        return 2;
    }

    const int sock = socket(AF_INET, SOCK_DGRAM, 0);
    if (sock == -1) {
        fprintf(stderr, "Can't create socket: %d", errno);
        return 3;
    }

    if (connect(sock, (struct sockaddr*) win_addr, sizeof(struct sockaddr_in)) == -1) {
        fprintf(stderr, "Can't send udp connection: %d", errno);
        return 4;
    }

    struct sockaddr_in lin_addr = {};
    socklen_t socklen = sizeof(lin_addr);
    if (getsockname(sock, (struct sockaddr*) &lin_addr, &socklen) != 0) {
        fprintf(stderr, "Can't get remote addr: %d", errno);
        return 5;
    }

    if (inet_ntop(AF_INET, &lin_addr.sin_addr, lin_ip, IP4_BUF_SIZE) == NULL) {
        fprintf(stderr, "Can't convert win to IP: %d", errno);
        return 6;
    }

    printf("%s:%s\n", win_ip, lin_ip); 
    return 0;
}
