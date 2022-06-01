class TC {
  
    fun fooBar(edit: (String) -> Unit) {
        runWriteActionAndWait { edit("") }
    }

    inline fun <T> runWriteActionAndWait(crossinline action: () -> T) {}
}