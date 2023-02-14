package test

import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

object PrivateClassRule {
    @ParameterizedTest
    @MethodSource("squares")
    fun foo() {}

    companion object {
        fun squares(): Stream<Int?>? {
            return null
        }
    }
}