
public class Test_ASPECT {
    public static final com.intellij.aspects.rt.pointcut.Pointcut test1_POINTCUT =
            new com.intellij.aspects.rt.pointcut.MethodCallPointcut(
                    new com.intellij.aspects.rt.pattern.BasicMethodPattern(
                            null,
                            new com.intellij.aspects.rt.pattern.ExactTypePattern("V"),
                            new com.intellij.aspects.rt.pattern.ExactTypePattern(new org.apache.bcel.generic.ObjectType(Point.class.getName())),
                            new com.intellij.aspects.rt.pattern.RegexpIdentifierPattern("setX"),
                            new com.intellij.aspects.rt.pattern.BasicTypeListPattern(
                            )
                    )
            );
}