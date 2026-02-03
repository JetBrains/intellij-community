// single import conflict
import sql.Date;
import <error descr="'sql.Date' is already defined in a single-type import">java.util.Date</error>;
import sql.*;
import java.util.*;
// multiple single-type import of the same class is fine
import java.io.IOException;
import java.io.IOException;


class c {}
