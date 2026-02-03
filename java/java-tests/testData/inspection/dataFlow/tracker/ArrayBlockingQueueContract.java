/*
Call always fails (new ArrayBlockingQueue<>(2, true, Arrays.asList("a", "b", "c")); line#10)
  According to hard-coded contract, constructor 'ArrayBlockingQueue' throws exception when 2 < size of Arrays.asList(...) (ArrayBlockingQueue<>; line#10)
 */
import java.util.concurrent.*;
import java.util.*;

public class ArrayBlockingQueueContract {
  void test() {
    BlockingQueue<String> piers = <selection>new ArrayBlockingQueue<>(2, true, Arrays.asList("a", "b", "c"))</selection>;
  }
}