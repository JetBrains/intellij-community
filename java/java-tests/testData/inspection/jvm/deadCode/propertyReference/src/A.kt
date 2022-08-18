class A {
    val jdk: String
        get() = "11"
}

fun main() {
    print(A().jdk)
}