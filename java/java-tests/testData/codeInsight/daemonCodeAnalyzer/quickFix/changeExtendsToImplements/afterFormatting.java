// "Change 'implements b' to 'extends b'" "true-preview"
class a extends b<String, Integer> {
}

class b<T, K> {}
