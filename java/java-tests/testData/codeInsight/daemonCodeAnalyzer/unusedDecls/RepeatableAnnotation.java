import java.lang.annotation.Repeatable;

@Repeatable(Alerts.class)
@interface Alert {
  String <warning descr="Method 'role()' is never used">role</warning>() default "/dev/null";
}
@interface Alerts {
  Alert[] value(); // gives unused declaration warning
}