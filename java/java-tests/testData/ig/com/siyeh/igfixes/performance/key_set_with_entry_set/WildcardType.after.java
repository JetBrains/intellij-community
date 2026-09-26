import java.util.*;

public class EntryIterationBug {
  public void putAll(Map<? extends SomeClass, ? extends List<SomeClass>> m){
    for(List<SomeClass> someClasses : m.values()){
      for(SomeClass wrapper2 : someClasses){
        put(wrapper2);
      }
    }
  }
  
  void put(SomeClass s) {}
  
  class SomeClass {}
}