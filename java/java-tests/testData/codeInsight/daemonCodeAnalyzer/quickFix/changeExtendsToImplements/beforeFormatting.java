// "Change 'implements b' to 'extends b'" "true"
class a implements <caret>b<String, Integer> {
}

class b<T, K> {}
