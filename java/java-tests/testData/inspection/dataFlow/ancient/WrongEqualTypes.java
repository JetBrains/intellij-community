import java.util.Calendar;

class Test {
  public void foo(Object c) {
    if (c instanceof Calendar) return;    
    if (c == getInstance()) {
      if (<warning descr="Condition 'c == null' is always 'true'">c == null</warning>) {}
    }
    if (<warning descr="Condition 'c == Calendar.getInstance()' is always 'false'">c == Calendar.getInstance()</warning>) {}
  }
  
  native Calendar getInstance();
}