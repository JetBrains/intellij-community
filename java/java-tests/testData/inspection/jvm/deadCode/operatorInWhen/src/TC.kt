class TC {
    operator fun F.contains(text: CharSequence): Boolean = text.isNotEmpty() 
    operator fun F2.contains(text: CharSequence): Boolean = text.isNotEmpty() 

    fun parse(str: String): String? {
        val fmt = when(str) {
            in F() -> "yyyy-MM-dd"
            else -> return null
        }
        return str
    }
  
    fun parse2(str: String): String? {
        val fmt = when {
            str in F2() -> "yyyy-MM-dd"
            else -> return null
        }
        return str
    }

}

class F
class F2

fun main() {
    TC().parse ("")
    TC().parse2 ("")
}