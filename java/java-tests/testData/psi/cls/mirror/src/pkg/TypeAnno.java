package pkg;

import java.io.Serializable;
import java.lang.annotation.ElementType;
import java.lang.annotation.Target;
import java.util.List;
import java.util.Map;

public interface TypeAnno<@TA(10) T extends @TA(11) Object> extends @TA(12) Serializable, @TA(13) Comparable<@TA(14) T> {
  @TA(-1) List<@TA String> get1TypeParam();

  @TA(-1) Map<@TA(0) String, @TA(1) String> get2TypeParams();

  @TA(-1) List<@TA(0) ? extends @TA(1) Object> getExtends();
  
  @TA(-1) List<@TA(0) ? super @TA(1) Object> getSuper();

  @TA(-1) List<@TA(0) ? extends @TA(1) List<@TA(2) ? super @TA(3) Object>> getExtendsNestedSuper();

  @TA(-1) List<@TA(0) ?> getInvariant();
  

  @TA(0) String @TA(1) [] getArray();

  @TA(0) String @TA(1) [] @TA(2) [] getArray2D();

  @TA(0) String @TA(1) [] @TA(2) [] @TA(3) [] getArray3D();
  
  @TA(0) List<@TA(1) String @TA(2) []> getArrayInList();
  
  @TA(0) List<@TA(1) ? extends @TA(2) String @TA(3) []> getExtendsArrayInList();
  
  @TA(0) List<@TA(1) ? super @TA(2) String @TA(3) []> getSuperArrayInList();

  <M extends @TA(0) CharSequence> @TA(1) M getTypeParameter();

  <M extends @TA(0) CharSequence & @TA(1) Serializable> @TA(2) M getTypeParameter2();
  
  <M extends @TA(0) Object & @TA(1) List<@TA(2) ? super @TA(3) String @TA(4) []>, N extends @TA(5) Object, P extends Object> @TA(6) M getTypeParameterComplex();
  
  <@TA(0) @TP(1) M extends @TA(2) CharSequence, @TA(3) N extends @TA(4) Number, @TP(5) P> @TA(6) P getTypeParametersAnnotated();

  @TA(0) Outer.@TA(1) Middle.@TA(2) Inner getInner();
  
  @TA(0) List<@TA(1) ? extends @TA(2) Outer.@TA(3) Middle.@TA(4) Inner> getInnerInTypeArgument();
  
  @TA(0) List<@TA(1) ? extends @TA(2) Outer.@TA(3) Middle.@TA(4) Inner @TA(5) []> getInnerInTypeArgumentAndArray();
  
  @TA(0) Outer<@TA(1) Outer>.
  @TA(2) Middle<@TA(3) Outer.@TA(4) Middle<@TA(5) ?>>.
  @TA(6) Inner<@TA(7) Outer.@TA(8) Middle<@TA(9) Outer>.@TA(10) Inner<?>> getInnerGeneric();
  
  @TA(0) Outer<@TA(1) Outer @TA(2) []>.
  @TA(3) Middle<@TA(4) ? extends @TA(5) Outer.@TA(6) Middle.@TA(7) Inner @TA(8)[]>.
  @TA(9) Inner<@TA(10) Outer<@TA(11)?>.@TA(12) Middle<@TA(13) ? super @TA(14) Outer>.@TA(15) Inner<@TA(16) ?> @TA(17) []> @TA(18) [] getMixed();
  
  void inThrows() throws @TA(0) Exception, @TA(1) Error;
  
  void inParameter(@TA(0) List<@TA(1) String> parameter1, @TA(2) List<@TA(3) String> parameter2);

  void inReceiver(@TA(0) TypeAnno<T> this);
  
  @TA(0) String field = "";
}

class Outer<O> {
  class Middle<M> {
    class Inner<I> {}
  }
}

@Target(ElementType.TYPE_USE)
@interface TA {
  int value() default 0;
}

@Target(ElementType.TYPE_PARAMETER)
@interface TP {
  int value();
}