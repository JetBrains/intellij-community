from test_helper import run_common_tests

if __name__ == '__main__':
    run_common_tests('''squares = [1, 4, 9, 16, 25]
print(squares)

print(slice)''',
                     '''squares = [1, 4, 9, 16, 25]
print(squares)

print()''', "Use slicing lst[index1:index2]")
