fun interface Cb {
    fun run(): Int
}

fun exec(cb: () -> Int): Int = cb()