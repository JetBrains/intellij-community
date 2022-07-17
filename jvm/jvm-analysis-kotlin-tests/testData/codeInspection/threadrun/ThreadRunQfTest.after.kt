import java.lang.Runnable
import java.lang.Thread

class ThreadRunQfTest {
    fun doTest() {
        val runnable = object : Runnable {
            override fun run() {}
        }
        val thread = Thread(runnable)
        thread.start()
    }
}