import org.<info descr="Not resolved until the project is fully loaded">springframework</info>.<info descr="Not resolved until the project is fully loaded">lang</info>.<info descr="Not resolved until the project is fully loaded">NonNull</info>;
import org.<info descr="Not resolved until the project is fully loaded">example</info>.<info descr="Not resolved until the project is fully loaded">lombokdumbmode</info>.<info descr="Not resolved until the project is fully loaded">model</info>.<info descr="Not resolved until the project is fully loaded">chain</info>.<info descr="Not resolved until the project is fully loaded">UserChain</info>;
import org.<info descr="Not resolved until the project is fully loaded">springframework</info>.<info descr="Not resolved until the project is fully loaded">boot</info>.<info descr="Not resolved until the project is fully loaded">autoconfigure</info>.<info descr="Not resolved until the project is fully loaded">Request</info>;
import <info descr="Not resolved until the project is fully loaded">a</info>.*;

@<info descr="Not resolved until the project is fully loaded">SpringBootApplication</info>
public class LombokDumbModeApplication {

    public static void main(String[] args) {
        <info descr="Not resolved until the project is fully loaded">Request</info> a = new <info descr="Not resolved until the project is fully loaded">Request</info>();
        <error descr="Incompatible types. Found: 'capture<?>', required: 'UserDao'">UserDao userDao = UserDao.builder()
                .id(1)
                .name("2")
                .surname("3")
                .email("4")
                .name("1")</error><error descr="';' expected">a</error>
                .id(1)
                .<info descr="Not resolved until the project is fully loaded">build</info>();

        String name = userDao
                .nhaame();
        <info descr="Not resolved until the project is fully loaded">UserChain</info> userChain = new <info descr="Not resolved until the project is fully loaded">UserChain</info>();
        String name1 = userChain.getName();
    }
}


class UserDao extends UserId {
    @<info descr="Not resolved until the project is fully loaded">NonNull</info>
    private final String nhaame;
    @<info descr="Not resolved until the project is fully loaded">NonNull</info>
    private final String surname;
    @<info descr="Not resolved until the project is fully loaded">NonNull</info>
    private final String email;

    protected UserDao(UserDaoBuilder<?, ?> b) {
        super(b);
        this.nhaame = b.nhaame;
        this.surname = b.surname;
        this.email = b.email;
    }

    public static UserDaoBuilder<?, ?> builder() {
        return new UserDaoBuilderImpl();
    }

    public String nhaame() {
        return this.nhaame;
    }

    public String surname() {
        return this.surname;
    }

    public String email() {
        return this.email;
    }

    public static abstract class UserDaoBuilder<C extends UserDao, B extends UserDaoBuilder<C, B>> extends UserIdBuilder<C, B> {
        private String nhaame;
        private String surname;
        private String email;

        public B name(String nhaame) {
            this.nhaame = nhaame;
            return self();
        }

        public B surname(String surname) {
            this.surname = surname;
            return self();
        }

        public B email(String email) {
            this.email = email;
            return self();
        }

        protected abstract B self();

        public abstract C build();

        public String toString() {
            return "UserDao.UserDaoBuilder(super=" + super.toString() + ", nhaame=" + this.nhaame + ", surname=" + this.surname + ", email=" + this.email + ")";
        }
    }

    private static final class UserDaoBuilderImpl extends UserDaoBuilder<UserDao, UserDaoBuilderImpl> {
        private UserDaoBuilderImpl() {
        }

        protected UserDaoBuilderImpl self() {
            return this;
        }

        public UserDao build() {
            return new UserDao(this);
        }
    }
}

abstract class UserId {
  private final long id;
  private final String info;

  protected UserId(UserIdBuilder<?, ?> b) {
    this.id = b.id;
    this.info = b.info;
  }

  public static abstract class UserIdBuilder<C extends UserId, B extends UserIdBuilder<C, B>> {
    private long id;
    private String info;

    public B id(long id) {
      this.id = id;
      return self();
    }

    public B info(String info) {
      this.info = info;
      return self();
    }

    protected abstract B self();

    public abstract C build();

    public String toString() {
      return "UserId.UserIdBuilder(id=" + this.id + ", info=" + this.info + ")";
    }
  }
}


