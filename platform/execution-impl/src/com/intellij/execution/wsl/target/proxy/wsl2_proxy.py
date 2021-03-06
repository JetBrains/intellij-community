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


def process(client_socket, client_address):
    remote_socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    remote_address = (remoteHost, remotePort)
    try:
        remote_socket.connect(remote_address)
        print("{0}: connected to {1}".format(client_address, remote_address), flush=True)
        t1 = threading.Thread(target=read_write, args=(client_socket, remote_socket, "client->remote"))
        t1.start()
        t2 = threading.Thread(target=read_write, args=(remote_socket, client_socket, "remote->client"))
        t2.start()
        t1.join()
        t2.join()
    except Exception as err:
        print("failed to proxy {0}<->{1}, caused by {2}".format(client_address, remote_address, err), flush=True)
    finally:
        print("{0}: disconnected from {1}".format(client_address, remote_address), flush=True)
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
        (_client_socket, _client_address) = serverSocket.accept()
        try:
            print("client connected from {0}".format(_client_address), flush=True)
            process(_client_socket, _client_address)
        finally:
            print("client from {0} is disconnected".format(_client_address), flush=True)
            _client_socket.close()
