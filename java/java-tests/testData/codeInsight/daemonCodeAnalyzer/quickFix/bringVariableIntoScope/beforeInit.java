// "Bring 'String con' into scope" "true-preview"
class a {
    private void ffff(String c){
        try { String con = null;  }
        finally {
            ffff(<caret>con);// This method doesn't mind con == null.
        }
    }
}