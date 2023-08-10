// "Replace with old style 'switch' statement" "true"
import java.util.*;

public class GenerateThrow {
  void foo(int i) {
    int res =   <caret>switch (i) { // convert to 'old style' switch
      case 0 -> 1;
      default/*1*/ ->/*2*/ throw /*3*/new IllegalArgumentException();
    };
  }
}