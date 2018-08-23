import org.jetbrains.annotations.Blocking
import org.jetbrains.annotations.NonBlocking

@NonBlocking
fun nonBlockingFunction() {
  <warning descr="Inappropriate blocking method call">blockingFunction</warning>();
}

@Blocking
fun blockingFunction() {}