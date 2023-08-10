public class YoYo {
    Object y;
    static class YoYoYo {
        private final YoYo anObject;
        private final Object y;
        Object x;
        Object[] xx;

        public YoYoYo(YoYo anObject, Object y) {
            this.anObject = anObject;
            this.y = y;
            this.x = y;
            this.xx = new Object[]{y};
        }

        void foo (){
            YoYo yoYoy = anObject;
            Object t = y;
            Object t1 = yoYoy.y;
        }
    }
}

