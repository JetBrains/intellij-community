class MyTest {
    {
        Appender data = Map::appendData;
    }
}

interface Map<B> {
    void appendData(String appender) ;
}

interface Appender {
    void append(Map<?> map, String appender);
}