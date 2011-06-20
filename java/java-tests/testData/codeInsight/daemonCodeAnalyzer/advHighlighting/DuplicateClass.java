// duplicates in extends

class a {
}

class b extends <error descr="Duplicate class: 'a'">a</error>, <error descr="Duplicate class: 'a'">a</error> {
}

interface i {}

class c implements <error descr="Duplicate class: 'i'">i</error>, <error descr="Duplicate class: 'i'">i</error> {
}