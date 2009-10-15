class X<T> {}
class Y extends X<String>{}
class Z<T> extends X<T>{}

class Main {
    X<?> a = new <caret>
}