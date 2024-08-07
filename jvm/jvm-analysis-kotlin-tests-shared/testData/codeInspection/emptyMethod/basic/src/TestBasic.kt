class TestBasic {
  fun empty() {}
  
  fun comment() {
    // comment
  }
  
  fun nonEmpty() {
    empty()
  }
  
  @Suppress("EmptyMethod")
  fun emptySuppressed() {
    
  }
}

fun topLevel() {
  
}