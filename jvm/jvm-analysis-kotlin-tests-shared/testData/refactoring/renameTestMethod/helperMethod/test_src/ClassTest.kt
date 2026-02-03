import org.junit.jupiter.api.Test

class ClassTest {
    @Test
    fun testCheckSome() {
    }

    private fun helperMethod<caret>(): Int {
        return 0
    }
}