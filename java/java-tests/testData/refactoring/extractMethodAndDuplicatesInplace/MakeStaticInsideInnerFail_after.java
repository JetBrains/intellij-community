class X {
    Runnable r = new Runnable() {
        @Override
        public void run() {
            extracted();
        }

        private void extracted() {
            System.out.println("hello");
        }
    };
}