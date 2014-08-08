from test_helper import run_common_tests, import_file, passed, failed


def test_value(path):
    file = import_file(path)
    if file.p_letter == "P":
        passed()
    failed("String index starts with 0.")


if __name__ == '__main__':
    run_common_tests('''python = "Python"
p_letter = type here
print(p_letter)
''', '''python = "Python"
p_letter =
print(p_letter)
''', "You should modify the file")

    import sys
    path = sys.argv[-1]
    test_value(path)
    #TODO check index operation used