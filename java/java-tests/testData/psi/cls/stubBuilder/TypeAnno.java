import java.io.Serializable;
import java.lang.annotation.ElementType;
import java.lang.annotation.Target;
import java.util.List;
import java.util.Map;

public interface TypeAnno<@TA(10) T extends @TA(11) Object> extends @TA(12) Serializable {
  @TA(-1) List<@TA String> get1TypeParam();

  @TA(-1) Map<@TA(0) String, @TA(1) String> get2TypeParams();

  @TA(-1) List<@TA(0) ? extends @TA(1) Object> getExtends();
  

  @TA(0) String @TA(1) [] getArray();

  @TA(0) String @TA(1) [] @TA(2) [] getArray2D();

  @TA(0) String @TA(1) [] @TA(2) [] @TA(3) [] getArray3D();
  
  @TA(0) List<@TA(1) String @TA(2) []> getArrayInList();

  <M extends @TA(0) CharSequence> @TA(1) M getTypeParameter();

  <M extends @TA(0) CharSequence & @TA(1) Serializable> @TA(2) M getTypeParameter2();

  
  void inThrows() throws @TA(0) Exception, @TA(1) Error;
  
  void inParameter(@TA(0) List<@TA(1) String> parameter1, @TA(2) List<@TA(3) String> parameter2);

  void inReceiver(@TA(0) TypeAnno<T> this);
  
  @TA(0) String field = "";
}

@Target(ElementType.TYPE_USE)
@interface TA {
  int value() default 0;
}