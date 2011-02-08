class A{
public abstract class IntegerPredicateBase implements Predicate{
 public boolean applies(Object obj){
  return <ref>applies((Integer)obj);  
 }
}
public interface Predicate{
 boolean applies(Object obj);
 boolean applies(Integer myObj);
}
 
}

