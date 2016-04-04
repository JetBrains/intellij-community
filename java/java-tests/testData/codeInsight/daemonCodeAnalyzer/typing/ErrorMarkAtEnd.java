class abcd
{
        private static void throwException(Object obj)
        {
	        new NullPointerException("NPE");
                throw  (obj == null) ? new NullPointerException("NPE")
                             : new RuntimeException("RTE");
    }

        public static void main(String[] args) throws Exception
        {
                try
                {
                        throwException(null);
                }
                catch (Throwable t)
                {
                        t.printStackTrace();
                }
                try
                {
                        throwException(new Object());
                }
                catch (Throwable t)
                {
                        t.printStackTrace();
                }<caret>
        }
}
