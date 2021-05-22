import sys
import threading
import socket


def read_write(src, dest, name):
    try:
        while 1:
            data = src.recv(1024)
            if not data or len(data) == 0:
                break
            dest.sendall(data)
    except Exception:
        pass
    dest.close()


def process(client_socket):
    remote_socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    try:
        remote_socket.connect((remoteHost, remotePort))
        t1 = threading.Thread(target=read_write, args=(client_socket, remote_socket, "client->remote"))
        t1.start()
        t2 = threading.Thread(target=read_write, args=(remote_socket, client_socket, "remote->client"))
        t2.start()
        t1.join()
        t2.join()
    finally:
        remote_socket.close()


if __name__ == '__main__':
    listenHost, remoteHost, remotePort = sys.argv[1], sys.argv[2], int(sys.argv[3])

    serverSocket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    serverSocket.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    serverSocket.bind((listenHost, 0))
    listenPort = serverSocket.getsockname()[1]
    print("IntelliJ WSL proxy is listening on port {0}, ready for connections".format(listenPort))
    print("{0}:{1} -> {2}:{3}".format(listenHost, listenPort, remoteHost, remotePort), flush=True)
    serverSocket.listen(10)

    while True:
        (_client_socket, client_address) = serverSocket.accept()
        try:
            print("client connected from {0}".format(client_address), flush=True)
            process(_client_socket)
        finally:
            _client_socket.close()
