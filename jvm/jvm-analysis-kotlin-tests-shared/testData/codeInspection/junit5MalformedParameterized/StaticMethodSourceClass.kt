package test

import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

class PrivateClassRule {
    @ParameterizedTest
    @MethodSource("squares")
    fun foo() {}

    fun squares(): Stream<Int?>? {
        return null
    }
}