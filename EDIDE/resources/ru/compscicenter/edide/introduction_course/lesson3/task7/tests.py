from test_helper import run_common_tests, import_file, passed, failed


def test_value(path):
    file = import_file(path)
    if file.first_half == '''\nit's a really long string\ntriple-quoted st''':
        passed()
    failed("Remember about string slicing.")


if __name__ == '__main__':
    run_common_tests('''phrase = """
it's a really long string
triple-quoted strings are used
to define multi-line strings
"""
first_half = type here
print(first_half)
''', '''phrase = """
it's a really long string
triple-quoted strings are used
to define multi-line strings
"""
first_half =
print(first_half)
''', "You should modify the file")

    import sys
    path = sys.argv[-1]
    test_value(path)
# TODO: check it's not hardcoded