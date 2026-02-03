import java.util.List;

interface Base<T> {
}

interface Middle<T> extends Base<List<? super T>> {
}

<error descr="'Base' cannot be inherited with different type arguments: 'java.util.List<? super T>' and 'java.util.List<T>'">interface Child<T> extends Middle<T>, Base<List<T>></error> {
}
