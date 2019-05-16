import org.jetbrains.annotations.*;

import java.util.*;
import java.util.stream.Stream;

public class LambdaAutoCloseable {
  boolean opened;

  void test3() throws Exception {
    opened = true;
    try( final AutoCloseable z = () -> {opened = false;}){
    }
    if (opened) {
      
    }
  }
}