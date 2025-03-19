import java.lang.Thread

class ThreadRunSuperTest : Thread() {
  fun doTest() {
    object : Thread("") {
      override fun run() {
        super.run()
      }
    }.start()
  }
}