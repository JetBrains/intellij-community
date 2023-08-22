// "Change 'implements b' to 'extends b'" "true-preview"
class a implements <caret>b<String, Integer> {
}

class b<T, K> {}
