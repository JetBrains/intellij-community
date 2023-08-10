class X {
    Runnable r = new Runnable() {
        @Override
        public void run() {
            extracted();
        }

        private static void extracted() {
            System.out.println("hello");
        }
    };
}