// single import conflict
import java.sql.Date;
<error descr="'java.sql.Date' is already defined in a single-type import">import java.util.Date;</error>
import java.sql.*;
import java.util.*;
// multiple single-type import of the same class is fine
import java.io.IOException;
import java.io.IOException;


public class c {}
