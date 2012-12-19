class TcpConnection extends ClientConnection {
    ConnectionEventDelegate<? extends ClientConnection> eventDelegate;
    {
        eventDelegate.<ClientConnection> onDisconnect<error descr="'onDisconnect(capture<? extends ClientConnection>)' in 'ConnectionEventDelegate' cannot be applied to '(TcpConnection)'">(this)</error>;
    }
}

class ClientConnection {}
interface ConnectionEventDelegate<T extends ClientConnection> {
    void onDisconnect(T t);
}