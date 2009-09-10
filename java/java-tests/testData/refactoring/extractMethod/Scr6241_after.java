import java.util.*;

class Test extends GregorianCalendar {
    public boolean isSaturday() {
        return newMethod() == SATURDAY;
    }

    private int newMethod() {
        return get( Calendar.DAY_OF_WEEK );
    }
}