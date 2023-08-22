import org.checkerframework.checker.tainting.qual.Untainted;

import java.util.List;

class LocalCheck {


  public void test(@Untainted List<String> clean, @Untainted List<String> cleanList2, @Untainted String t, String dirty) {
    sink(t);
    sink(clean.get(0));
    List<String> list1 = clean;
    List<String> list2 = clean;
    update(list1); //not highlighted in current realisation, might be changed
    list2.add(dirty);  //not highlighted in current realisation, might be changed
    sink(<warning descr="Unknown string is used as safe parameter">list1.get(0)</warning>); //warn
    sink(<warning descr="Unknown string is used as safe parameter">list2.get(0)</warning>); //warn
    sink(clean.get(0));
    List<String> list3 = cleanList2;
    sink(list3.get(0));
    sink(<warning descr="Unknown string is used as safe parameter">dirty</warning>); //warn

    String clean2 = t;
    sink(t);
    clean2 = dirty;
    sink(<warning descr="Unknown string is used as safe parameter">clean2</warning>); //warn

    String toDirty = t;
    sink(toDirty);

    new Runnable() {
      @Override
      public void run() {
        sink(toDirty);
      }
    };

    Runnable runnable = () -> sink(toDirty);
  }

  private void update(List<String> list) {
    list.add("1");
  }

  public void sink(@Untainted String clean) {

  }
}
