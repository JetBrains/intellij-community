// "Bring 'String con' into scope" "true-preview"
class a {
    private void ffff(String c) {
        String con = null;
        try {
            con = null;
        } finally {
            ffff(con);// This method doesn't mind con == null.
        }
    }
}