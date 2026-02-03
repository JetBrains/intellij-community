public class aa {
    public static boolean isVis<caret>ualUpdate(int update_type) {
        return update_type <= Iaaa.FULL_QUOTE_VISUAL;
    }
}

interface Iaaa {
    int FULL_QUOTE_VISUAL = 0;
}

class a implements Iaaa {
    int update_type = 8;

    public static boolean blabla(int update_type) {
        return update_type <= FULL_QUOTE_VISUAL;
    }
}