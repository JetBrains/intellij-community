import java.lang.Runnable
import java.lang.Thread

class ThreadRunTest {
  fun doTest() {
    val runnable = object : Runnable {
      override fun run() {}
    }
    val thread = Thread(runnable)
    thread.<warning descr="Calls to 'run()' should probably be replaced with 'start()'">run</warning>()
  }
}