// "Collapse repeating annotations" "true"

@XYZ(data = {1, 2, 3, 4})
@XYZ(5)
class X{}
@interface XYZ {
  int value() default 0;
  
  int[] data();
}