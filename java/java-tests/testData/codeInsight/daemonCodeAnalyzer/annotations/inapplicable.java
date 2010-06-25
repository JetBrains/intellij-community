import java.lang.annotation.*;

//OK
@Target({ElementType.TYPE})
@Expose @interface Expose {}

@Target({ElementType.FIELD})
@<error descr="'@Expose1' not applicable to annotation type">Expose1</error>@interface Expose1 {}