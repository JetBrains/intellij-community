class V {
    operator fun get(i: Int): Int = i
    infix fun combine(o: V): V = o
}