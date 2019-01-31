import java.util.Calendar;

class Test {
  public void foo(Object c) {
    if (c instanceof Calendar) return;    
    if (c == Calendar.getInstance()) {}
  }
}