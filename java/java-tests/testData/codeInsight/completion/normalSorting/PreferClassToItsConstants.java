class Util {
  void foo() {
    MyCalendar.getInstance().get(MyCale<caret>x)
  }

}

class MyCalendar {
  static MyCalendar getInstance() {}
  
  int get(int field) {}
  
  public static final int FIELD_COUNT;
  public static final int AM;
  public static final int PM;
}