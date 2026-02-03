class A {
    val jdk: String
        get() = "11"
    val jdk1: String
        get() = "11"
}

fun main() {
    print(Runnable { A().jdk }.run())
    print(object : Runnable {
        override fun run() {
            A().jdk1
        }
    }.run())

}