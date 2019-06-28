// "Collapse repeating annotations" "true"

@X<caret>YZ(data = 1)
@XYZ(data = {2, 3})
@XYZ(data = {4})
@XYZ(5)
class X{}
@interface XYZ {
  int value() default 0;
  
  int[] data();
}