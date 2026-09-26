import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

@OptIn(ExperimentalContracts::class)
fun isStr(x: Any?): Boolean {
    contract { returns(true) implies (x is String) }
    return x is String
}