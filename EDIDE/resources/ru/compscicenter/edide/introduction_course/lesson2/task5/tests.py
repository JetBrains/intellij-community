from test_helper import run_common_tests, import_file, passed, failed


def test_value(path):
    file = import_file(path)
    if file.number == 14.0:
        passed()
    failed("Use += operator")


if __name__ == '__main__':
    run_common_tests('''number = 9.0

number operator 5

print(number)''', '''number = 9.0

number  5

print(number)''', "You should modify the file")

    import sys
    path = sys.argv[-1]
    test_value(path)