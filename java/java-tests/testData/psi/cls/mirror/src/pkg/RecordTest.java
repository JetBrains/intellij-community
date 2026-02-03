package pkg;

import java.lang.annotation.Target;
import java.lang.annotation.ElementType;
import java.util.List;

public record RecordTest(@RA(1) int x, @RA(2) @TA(3) List<@TA(4) String> list, @MA double d, @PA String... varArg) {}
@Target(ElementType.TYPE_USE)
@interface TA {int value();}
@Target(ElementType.RECORD_COMPONENT)
@interface RA {int value();}
@Target(ElementType.PARAMETER)
@interface PA {}
@Target(ElementType.METHOD)
@interface MA {}