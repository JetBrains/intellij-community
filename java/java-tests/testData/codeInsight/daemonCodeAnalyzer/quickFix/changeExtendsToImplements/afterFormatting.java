// "Change 'implements b' to 'extends b'" "true"
class a extends b<String, Integer> {
}

class b<T, K> {}
