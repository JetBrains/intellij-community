package test

import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
object PrivateClassRule {
    @ParameterizedTest
    @MethodSource("squares")
    fun foo() {}

    object {
        @JvmStatic
        fun squares(): Stream<Int?>? {
            return null
        }
    }
}